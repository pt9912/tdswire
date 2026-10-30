# Slice slice-adr-fehler-attention: ADR-0007 entscheiden: Fehlerbehandlung und ATTENTION-Semantik

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-044 · SPEC-047 · SPEC-048 · SPEC-049 · SPEC-029 · SPEC-031 · SPEC-011 · SPEC-016 · ARC-015

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** [ADR-0007](../../adr/0007-fehler-und-attention.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Fehlerklassen, die eine Wiederverwendung der Verbindung erlauben; ob nach `TDS_SHUTDOWN` und nach einem internen Fehler (`SPEC-048`) die Verbindung stets endet, wie `SPEC-044` und `SPEC-049` es vorsehen, oder die Recovery-Regel Ausnahmen kennt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [ ] [ADR-0007](../../adr/0007-fehler-und-attention.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [ ] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `docs/plan/adr/0007-fehler-und-attention.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** für [ADR-0007](../../adr/0007-fehler-und-attention.md) (Stand 2026-10-30; die ADR bleibt `Proposed` bis Review und Verifikation). Spike per Dockerfile ohne Mount, `Microsoft.Data.SqlClient` 7.1.1 auf `dotnet/sdk:10.0.403`, `Encrypt=false`, `Pooling=false` (außer R18/R19), eine Verbindung je Szenario. Zwei Messobjekte, in der ADR getrennt ausgewiesen: **R**, der echte SQL Server 2022 (Digest `sha256:cf8f0ab2…`, 16.00.4305) hinter einem mitschneidenden TCP-Proxy im Client-Container, und **T**, ein in C# geschriebener Testserver mit skriptbaren Antworten. R sagt, was ein echter Server sendet; T sagt, wie der Referenzclient auf eine bestimmte Antwort reagiert. Es lief kein Server dieses Repos. Der Spike-Code liegt nur im Scratchpad und ist nicht im Repo; Roh-Logs sind nicht abgelegt, die ADR trägt die Zusammenfassung. Aus den Messungen ergab sich:

- R (ein Lauf je Zeile, R zweimal gefahren, Verhalten gleich): Fehler der Class ≤ 19 mit ERROR und DONE[ERR] lassen die Verbindung offen und die Folgeabfrage antwortet richtig; ab Class 20 sendet der Server weitere Fehler (2745, 596) und schließt. Statement-abbrechende Fehler (8134) lassen den Rest des Batches laufen, Batch-abbrechende (208, `THROW`, `XACT_ABORT`) nicht. Eine offene Transaktion überlebt einen Fehler ohne `XACT_ABORT`. Cancel und Timeout beantwortet der Server mit zwei Nachrichten (abgebrochene Antwort mit DONE[ERR], dann DONE[ATTN]); die Verbindung bleibt offen. Im gemessenen Deadlock wiederholte weder Server noch Client.
- T (ein Lauf je Zeile): Der Client schließt ab Class 20 selbst; fehlendes DONE, DONE ohne ATTN-Bit als Bestätigung oder eine ignorierte ATTENTION lassen den Client bis zum Timeout hängen (plus rund 5 s Wartezeit auf die Bestätigung bei ignoriertem ATTENTION ohne Antwort), danach ist die Verbindung geschlossen.
- Nicht gemessen: TDS 8.0 und TLS, Client-Disconnect mitten in der Anfrage, ATTENTION-Race im Moment des letzten DONE, Pooling mit vom Server geschlossener Verbindung, andere Clients, ein Server dieses Repos.
- Abhängigkeit von [ADR-0004](../../adr/0004-session-zustandsautomat.md) (noch nicht entschieden, parallel gemessen): ATTENTION in jedem Anfragezustand, Sitzungszustand nach Fehler, `RESETCONNECTION`, Zeitpunkt des Schließens bei `TDS_SHUTDOWN`. Die Stellen sind in der ADR benannt; die Entscheidung dieser ADR sollte nach [ADR-0004](../../adr/0004-session-zustandsautomat.md) fallen oder diese Punkte offen lassen.
- **Entscheidung des Nutzers (2026-10-30):** enge Form von Option B mit A als Grundregel. Die Verbindung bleibt offen nur bei Class ≤ 19 mit vollständigem ERROR und DONE[ERR] sowie nach Cancel oder Timeout mit gesendetem DONE[ATTN]; bei Class ≥ 20, `TDS_INTERNAL_ERROR` (`SPEC-048`) und `TDS_SHUTDOWN` (`SPEC-049`) schließt der Server nach ERROR und DONE[ERR], bei nicht mehr sicher abschließbarem Strom (`SPEC-031`) schließt er (ob davor ERROR und DONE gesendet werden, ist offen). Gemessen sind nur Class 20 und 25; `SPEC-048`/`SPEC-049` sind Setzung. [ADR-0007](../../adr/0007-fehler-und-attention.md) trägt Messung, Entscheidung und die offenen Punkte (Cancel und Timeout als Fehler, Fehlersemantik im Strom, ATTENTION im Handshake, Ende des Handshakes nach abgelehntem Login, Class der eigenen Fehlercodes, Inhalt der Cancel-Antwort); `TDS-FA-06.a`, `SPEC-029`, `SPEC-048` und `SPEC-049` sind nachgezogen.
- Die ADR ist nicht `Accepted`: Review und Verifikation stehen aus, und die Annahme folgt erst, wenn [ADR-0004](../../adr/0004-session-zustandsautomat.md) entschieden ist.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-session-zustand` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Fehler-/Cancel-Matrix zeigt Desynchronisation, die eine Folge-ADR klären muss.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Fehler-/Cancel-Matrix zeigt Desynchronisation, die eine Folge-ADR klären muss. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
