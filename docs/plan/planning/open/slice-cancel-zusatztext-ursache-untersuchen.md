# Slice slice-cancel-zusatztext-ursache-untersuchen: Ursache des Zusatzes „ Operation cancelled by user.“ in der Antwort auf `Cancel()` klären (Rest von A-7)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice des Nutzerentscheids vom 2026-11-20 im Slice [slice-cancel-antwort-zwei-nachrichten-angleichen](../done/slice-cancel-antwort-zwei-nachrichten-angleichen.md): A-7 bleibt als eine Regel, die Ursache des Zusatzes ist ein eigener Slice).

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Scope: Abgleich mit dem echten Server), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Abbruch), [ADR-0054](../../adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) (Zwei-Nachrichten-Form, Erwartung zu A-7), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste; nur, wenn die Regel nach dem Befund ihre Art ändert) · [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (nur, wenn der Befund die Form der Antwort ändert; das entscheidet der Nutzer, nicht dieser Slice) · [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 5 Nr. 12.

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-20.

---

## 1. Ziel und Abgrenzung

**Ziel:** Die Bedingung ist geklärt und belegt, unter der `Microsoft.Data.SqlClient` 7.1.1 dem Grundtext „A severe error occurred on the current command.  The results, if any, should be discarded.“ den Zusatz „ Operation cancelled by user.“ anhängt: am Server dieses Repos in jedem gemessenen Teillauf, an der Referenz (2022 und 2025) in 1 von 21 Teilläufen.

**Ist-Stand** (gemessen laut [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), Abschnitt „Antwort auf ATTENTION: Zwei-Nachrichten-Form“; was nicht als gemessen steht, ist gelesen):

- **Messung:** Schritt `cancel-mit-folgeabfrage` des Live-Laufs; der Server zeigt den Zusatz in 21 von 21 Teilläufen (13 Läufe am 2026-11-19, 4 Läufe am 2026-11-20), die Referenz in 1 von 21 (`live-2025`, 2026-11-20, Lauf 2); in früheren Läufen vom 2026-11-06 zeigten beide Instanzen beide Varianten (Zählung ohne abgelegte Ausgabe). `Number 0, Class 11, State 0` und der Grundtext sind in allen Fällen gleich.
- **Was am Draht gemessen ist:** keine Nachricht der Referenz trägt einen ERROR-Token mit diesem Text; beide Texte entstehen im Client aus der Form der Antwort (zwei Nachrichten, `DONE[ERROR]` ohne ERROR-Token, `DONE[ATTN]`). Der Server dieses Repos sendet dieselbe Form (Mitschnitt-Lauf grün, Handvektoren aus den Aufnahmen beider Instanzen).
- **Nicht untersucht:** *wann* der Client den Zusatz ergänzt. Zwei Vermutungen stehen im Messprotokoll, beide **ohne Beleg**: die zeitliche Lage der beiden Nachrichten beim Client (der Server schreibt sie unmittelbar hintereinander, bei der Referenz ist der Abstand nicht gemessen) und eine Eigenschaft der Form selbst.
- **Folge für den Sensor:** die Live-Liste führt für den Schritt eine Regel (Referenz ohne, Server mit Zusatz); zeigt die Referenz den Zusatz, sind beide Texte gleich, die Regel entfällt und `LAUF=live` ist rot. `LAUF=live` war am 2026-11-20 in drei von vier Läufen grün.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Änderung der Form der Antwort oder des Zeitpunkts, zu dem der Server die zweite Nachricht schreibt — die Form ist durch [ADR-0054](../../adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) und [ADR-0057](../../adr/0057-abbruch-beim-beenden-endet-im-paar.md) entschieden; ein Befund, der sie berührt, ist eine Entscheidung des Nutzers und gegebenenfalls eine Folge-ADR, kein Liefer-Punkt dieses Slice.
- Das Umbauen der Live-Liste auf eine Regel, die beide Texte annimmt — das wäre ein Lockern des Sensors ([AGENTS.md](../../../../AGENTS.md) §3.6) und braucht die Entscheidung des Nutzers; dieser Slice liefert nur den Befund und die Optionen.
- Andere Clients als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc) — nicht gemessen und kein Gegenstand von [slice-mssql-jdbc-gegen-testserver](slice-mssql-jdbc-gegen-testserver.md).
- SESSIONSTATE (A-18) und das Abspielen des Abbruchs eines `WAITFOR`-Befehls (A-15) — anderer Mechanismus beziehungsweise anderer Slice ([slice-simulation-anweisungsformen-erweitern](../in-progress/slice-simulation-anweisungsformen-erweitern.md)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messung der Bedingung:** das Messprotokoll nennt, in wie vielen Läufen je Instanz die Referenz den Zusatz zeigt und in wie vielen nicht (Zahl der Läufe, Datum, Host, Last, Rohausgabe der Läufe abgelegt oder die Lücke benannt), und was die Läufe mit und ohne Zusatz am Draht unterscheidet (Bytes und zeitliche Lage der beiden Nachrichten, soweit der mitschneidende Proxy sie zeigt).
- [ ] **Ursache:** die Ursache des Zusatzes ist benannt und belegt (Messung oder Quelle des Clients mit Fundstelle), oder die Aussage „nicht festgestellt“ steht mit den Grenzen der Messung da; das Messprotokoll nennt die Optionen für den Nutzer (Ausnahmeliste, Server, nichts) mit je ihrer Wirkung auf den Lauf, ohne eine zu wählen.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec); `make spec-abdeckung` nur bei Änderung von Tests oder `@spec`-Tags. Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-7), `live-ausnahmen.tsv` (nur, wenn der Nutzer nach dem Befund eine Änderung entscheidet), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) (Nr. 12).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-20, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/messungen/referenzabgleich.md` | update | Messreihe zur Bedingung des Zusatzes mit Zahl, Datum, Host, Last; Ursache oder „nicht festgestellt“; Optionen |
| `referenztest-dotnet/Vergleich/` (Mitschnitt- und Live-Lauf), `tools/vergleichslauf-d.sh` | update (falls der Messweg es braucht) | Wiederholung des Schritts `cancel-mit-folgeabfrage` mit gesicherter Ausgabe je Lauf und der Bytes der beiden Nachrichten mit Zeitstempeln; kein Gate |
| `docs/plan/kompatibilitaetsmatrix.md`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | update (nur nach Entscheidung des Nutzers) | Stand der Regel A-7 |

**Messweg (Vorschlag des Planners, keine Festlegung; der Implementer prüft, was der vorhandene Proxy hergibt):** (a) den Schritt `cancel-mit-folgeabfrage` an beiden Instanzen so oft wiederholen, dass eine Rate je Instanz angegeben werden kann, jede Ausgabe je Lauf gesichert; (b) in denselben Läufen den mitschneidenden TCP-Proxy der Mitschnitt-Läufe ([Methode](../../../messungen/referenzabgleich.md)) mitlaufen lassen und die Bytes und Ankunftszeiten der beiden Nachrichten im Fall mit und im Fall ohne Zusatz gegenüberstellen; (c) dieselbe Gegenüberstellung am Server dieses Repos, einschließlich des Abstands, mit dem er die zweite Nachricht schreibt; (d) falls (a) bis (c) nichts entscheiden, die Quelle des Clients (`Microsoft.Data.SqlClient` 7.1.1) auf die Stelle lesen, die den Text bildet, mit Fundstelle ([AGENTS.md](../../../../AGENTS.md) §3.8: keine Abschnittsnummer ohne belegbare Quelle).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`). Der Slice braucht die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe. Er hängt nicht an einem anderen Slice; [slice-cancel-antwort-zwei-nachrichten-angleichen](../done/slice-cancel-antwort-zwei-nachrichten-angleichen.md) hat die Form gebaut und liegt vor diesem Start in `done/`.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): die Referenzinstanzen sind nicht beziehbar, oder der Messweg (b) liefert am vorhandenen Proxy keine Zeitstempel und der Aufbau eines anderen ist ein eigener Slice.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, das Messprotokoll nennt Rate, Befund und Optionen mit Zahlen und Datum, die Entscheidung des Nutzers über die Folge ist eingeholt und im Slice festgehalten (nicht Liefer-Punkt, sondern Bedingung der Closure), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Der Zusatz erscheint bei der Referenz selten (1 von 21 Teilläufen); eine Messreihe in vertretbarer Laufzeit sieht ihn möglicherweise nie oder zu selten für einen Vergleich. — **Ausgang:** offen bis zur Closure; die Zahl der nötigen Läufe ist vor der Reihe abzuschätzen (Laufzeit je Lauf 68 bis 83 s am 2026-11-20), und „nicht festgestellt“ mit der Zahl der Läufe ist ein zulässiger Ausgang.
- Die Ursache liegt im Client (Quelle nicht im Repo) und ist am Draht nicht sichtbar. — **Ausgang:** offen bis zur Closure; Messweg (d) ist die Rückfalloption, ihre Fundstelle muss belegbar sein.
- Bis zur Entscheidung des Nutzers bleibt `make vergleichslauf-d LAUF=live` gelegentlich rot ohne Befund des Servers. — **Ausgang:** offen bis zur Closure; das Messprotokoll nennt es, ein roter Lauf ohne weitere Abweichung ist kein Befund des Servers.
- Größe: zwei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Nutzerentscheid 2026-11-20 im Slice [slice-cancel-antwort-zwei-nachrichten-angleichen](../done/slice-cancel-antwort-zwei-nachrichten-angleichen.md) (A-7 bleibt eine Regel, die Ursache des Zusatzes ist ein eigener Folge-Slice); Verifikation dieses Slice, Finding F-1 und F-4 ([Verifikationsbericht](../../../reviews/2026-11-20-slice-cancel-antwort-zwei-nachrichten-angleichen-verifikation.md)); Register `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`; [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), Abschnitt „Antwort auf ATTENTION: Zwei-Nachrichten-Form“.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `cancel-antwort-text-weicht-vom-echten-server-ab`, `messlauf-intermittierend-rot-ohne-rate-im-protokoll`, `einmal-rot-ohne-gesicherte-ausgabe`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
