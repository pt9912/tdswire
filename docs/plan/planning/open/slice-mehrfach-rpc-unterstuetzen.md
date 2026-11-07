# Slice slice-mehrfach-rpc-unterstuetzen: Mehrere RPCs in einer Nachricht unterstützen (Batch-Flag der RPC-Nachricht)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung (Vorrat, kein Closure-Kriterium der Welle, Nutzerentscheid 2026-11-07)

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) (`Accepted`; der Mehrfach-RPC liegt dort auf der Seite ERROR und DONE, bis dieser Slice ihn unterstützt)

**Berührte Spec-Stellen:** SPEC-011 (Parserfehler) · SPEC-030 (RPC-Varianten, Trennlinie des Abschlusses, Zuordnung des Mehrfach-RPC) · SPEC-013 · Abschnitt „RPC im Backend-Vertrag“ (eine Nachricht, mehrere Aufrufe: Reihenfolge der Antworten und Ordinal je Aufruf). Die Spezifikation ändert dieser Slice bei der Umsetzung, nicht der Plan.

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

**Ziel:** Der Server nimmt eine RPC-Nachricht mit mehreren RPCs an (Batch-Flag der RPC-Nachricht, am Referenzclient `SqlDataAdapter` mit `UpdateBatchSize` 2) und beantwortet sie so, wie es der echte SQL Server tut, statt sie als Protokollfehler (`TDS_PROTOCOL_ERROR`, Class 20, Verbindung geschlossen) abzuweisen.

**Eingang (Herkunft).** Am echten Server ist `UpdateBatchSize 2` gültig; der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` (`make e2e` Stufe 6) pinnt das Verhalten dieses Servers, keinen Messbefund. [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) ordnet den Fall der Seite ERROR und DONE zu und nennt die Unterstützung ein eigenes Planungsthema; Nutzerentscheid vom 2026-11-07. Die Antwortform des echten Servers auf eine Nachricht mit mehreren RPCs (Reihenfolge der Abschlüsse, RETURNSTATUS und RETURNVALUE je Aufruf, Ordinal) ist **nicht gemessen** und die erste Aufgabe dieses Slice.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Trennlinie des Abschlusses für Struktur- und Kodierfehler (ERROR und DONE) — Gegenstand von [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md); dieser Slice nimmt nur den Mehrfach-RPC aus der Seite ERROR und DONE heraus, wenn er ihn unterstützt (dann ist der Fall kein Fehler mehr).
- Eine Antwort des echten Servers auf eine ungültig kodierte RPC-Nachricht — kein Messauftrag (Nutzerentscheid 2026-11-07, akzeptiertes Negativ der ADR).
- Weitere Formen außerhalb des Katalogs von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (`sp_prepare`, `sp_unprepare` als RPC, Cursor): andere Gegenstände.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei; Vorschlag, bei der Priorisierung zu bestätigen):

- [ ] **Messung:** die Antwort des echten SQL Servers auf eine RPC-Nachricht mit mehreren RPCs (`UpdateBatchSize` 2, beide Instanzen) am Draht aufgezeichnet: Reihenfolge von DONEINPROC, DONEPROC, RETURNSTATUS, RETURNVALUE je Aufruf, Statusbits, Ordinal (Messprotokoll `docs/messungen/referenzabgleich.md`); der Erzeuger der Messung liegt im Repo (Muster `referenztest-dotnet/Vergleich/MessungWireTests.cs`, Mitschnitt unter `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`), kein Wegwerf-Prototyp.
- [ ] **Spezifikation und Bau:** Parser und Dispatch nehmen mehrere RPCs an und rufen das Backend je Aufruf; die Spezifikation führt die gemessene Antwortform; die Zuordnung des Mehrfach-RPC zur Seite ERROR und DONE in `SPEC-030` entfällt.
- [ ] **Nachweis:** der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` wird durch einen Test ersetzt, der `UpdateBatchSize` 2 gelingen lässt (Begründung im Commit, kein stilles Löschen); Matrix (Lücke 4) und `make e2e` auf den neuen Stand.

Nachweis-Pflichten für alle Punkte:

- [ ] Jede Aussage steht an einem Test, einem Mitschnitt oder ist als Festlegung gekennzeichnet.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Backend-Vertrag: ein RPC je Aufruf).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` (Parser, `RpcParameterReader.kt`) und `protocol/dispatch/` (`RpcBinder.kt`, `RequestLoop.kt`) | update | mehrere RPCs einer Nachricht lesen und je Aufruf an das Backend geben (Ansatz nach der Messung) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/` | update | Antwortform je Aufruf nach der Messung |
| `spec/spezifikation.md` (`SPEC-011`, `SPEC-030`, Backend-Vertrag), `docs/plan/kompatibilitaetsmatrix.md` | update | gemessene Lesart, Streichung der Zuordnung des Mehrfach-RPC |
| `referenztest-dotnet/` (Lücke 4 der Matrix), `tools/e2e.sh` | update | Test mit `UpdateBatchSize` 2 gelingt; Messfall für das Messprotokoll |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Reihenfolge: erst die Messung (sie bestimmt Antwortform und Ordinal), dann Spezifikation, dann Bau.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Priorisierung durch den Nutzer und ein Verantwortlicher im Kopf; die Messung am echten Server braucht die EULA-Bestätigung des Nutzers für den Lauf (`make vergleichslauf-d`, nur lokal).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Messung zeigt eine Antwortform, die den Sequenzierer über den Mehrfach-RPC hinaus umbaut; Schnitt: Messung als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Backend-Port trägt keine Aufrufreihe (ein `QueryRequest` je Nachricht); der Nutzer entscheidet über die Form des Ports.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, `make e2e` grün mit gelingendem `UpdateBatchSize` 2, Review-Report liegt vor, Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Antwortform ungemessen.** Ohne Messung bliebe jede Reihenfolge Setzung (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.
- **Port-Form.** Ein Aufruf je `QueryRequest` kann die Aufrufreihe einer Nachricht nicht abbilden, wenn das Backend sie als Einheit sehen soll. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt Bibliothek, Spezifikation und Testwerkzeug. `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste, 2026-11-07). Treffer: `BEO-TDS/parserfehler-einer-rpc-nachricht-endet-in-done-statt-doneproc` (gestrichen durch [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md); der Gegenstand dieses Slice ist sein Eingang) und `BEO-TDS/aussagen-ueber-messung-hinaus` (die Antwortform braucht die Messung vor dem Bau).

**Modus-Begründungsblock.** Alle berührten Sub-Areas GF.
