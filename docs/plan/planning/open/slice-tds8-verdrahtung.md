# Slice slice-tds8-verdrahtung: TDS-8.0-Pfad ab dem ersten Byte im Serverprozess verdrahten

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage — der Slice dient der Messung am Referenzclient (`slice-qa-kompatibilitaet`, Welle `welle-qualitaet-grundlage`) und ändert den Dispatch der Welle `welle-anfrageverarbeitung` nicht; läge er dort, schlösse deren Closure erst mit einem Slice, der zu ihrer Aussage (Rohbytes-Folge ab `READY`) nichts beiträgt.

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-020 · SPEC-023

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-31.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Pfad ab dem ersten Byte (TDS 8.0, vorgelagertes TLS) ist im Serverprozess verdrahtet, belegt durch eine Rohbytes- und eine JDK-Gegenstelle gegen den Prozess, und das KDoc von `runtime/ConnectionWiring.kt` beschreibt ihn als Ist-Zustand statt als nicht verdrahtet.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Messung am Referenzclient `Microsoft.Data.SqlClient` und Hebung der Codec-Aussage von *Festlegung* auf *gemessen* — `slice-qa-kompatibilitaet`; dieser Slice belegt den Pfad nur gegen eine Rohbytes- und eine JDK-Gegenstelle.
- Der Anfragepfad im Produktionsprozess (Simulations-Backend verdrahten) — `slice-sim-aenderung-integration`; dieser Slice betrifft nur den Weg vom ersten Byte bis zum Handshake.
- Eine neue TLS-Konfiguration, Suiten oder Protokollversionen — Bestand aus [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) und `SPEC-023`; der Slice verdrahtet den vorhandenen Pfad.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **TDS-8.0-Pfad im Serverprozess** (`SPEC-001`, `SPEC-020`): Eine Verbindung, die mit TLS beginnt (`CONNECTED → TLS_NEGOTIATION → PRELOGIN`), wird im Serverprozess angenommen, durchläuft Handshake und Login bis `READY` und wird wie der TDS-7.4-Pfad beendet; ein Test mit einer JDK-Gegenstelle und ein Rohbytes-Test belegen den Pfad am laufenden Prozess, einschließlich des Verhaltens bei Klartext als erstem Byte in diesem Modus (Festlegung des Slice, nicht gemessen). Die Wahl des Pfads je Verbindung (erstes Byte) und ein Konfigurationsschalter, falls nötig, sind Festlegungen des Slice und als solche gekennzeichnet.
- [ ] **KDoc als Ist-Zustand:** Das KDoc von `runtime/ConnectionWiring.kt` beschreibt den verdrahteten Pfad statt „nicht verdrahtet“; die Aussage trägt höchstens so weit wie der Test (`AGENTS.md` §3.8): gemessen ist nur die Gegenstelle des Tests, nicht der Referenzclient.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Konfiguration, `SPEC-020`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` (`ConnectionWiring.kt`, Listener) | update | Pfad ab dem ersten Byte verdrahten, KDoc nachziehen |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` | neu | Happy (JDK-Gegenstelle bis `READY`), Negative (Klartext im TDS-8.0-Modus), Rohbytes-Folge nach [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md) und [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md) liegen in `done/` (erfüllt), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) wird geändert und stellt den Pfad ab dem ersten Byte infrage.

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

- Der Pfad ist nur gegen eine Rohbytes- und eine JDK-Gegenstelle belegt; ob der Referenzclient ihn annimmt, ist nicht gemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, Träger `slice-qa-kompatibilitaet`). — **Ausgang:** offen bis zur Closure.
- Ob beide Pfade am selben Port laufen (das erste Byte unterscheidet) oder an zwei Ports, ist beim Start festzulegen und nicht gemessen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `runtime` ist ein Pfad-Cluster, trägt aber keine eigene Strukturregel und keine eigene Inventur-Linie: unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — geplant (Träger `slice-qa-kompatibilitaet`; dieser Slice ist die Voraussetzung der TDS-8.0-Messung; ein Beleg aus seiner Closure wird als Evidence ergänzt). Ohne Treffer: die übrigen Beobachtungen.

Alle berührten Sub-Areas GF.
