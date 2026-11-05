# Slice slice-testserver-manuell-doku: Anleitung zum manuellen Test des Testservers: Befehle, Port, Anmeldung, TLS, Beispiele, Grenzen


**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md).

**Berührte Spec-Stellen:** —

**Verantwortlich:** —

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine Anleitung, nach der ein Mensch ohne Vorwissen den Testserver mit `make testserver-up` startet, einen eigenen Client anmeldet, Anfragen stellt und mit `make testserver-down` stoppt, mit den Grenzen dessen, was der Testserver kann.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Targets selbst: [slice-testserver-up-down](../in-progress/slice-testserver-up-down.md); diese Doku beschreibt gebaute Targets und beginnt erst, wenn der Slice in `done/` liegt.
- Die Einbettung der Bibliothek in eine eigene Anwendung und die Release-Schritte: [slice-release-doku-prozess](slice-release-doku-prozess.md); der Testserver ist dort nur ein Verweis.
- Der Nachvollzug von einem frischen Klon durch eine zweite Person: Closure-Trigger der Welle [welle-testserver-manuell](../welle-testserver-manuell.md), nicht dieses Slice.

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

- [ ] Die Anleitung (Ort: Vorschlag `docs/user/testserver-manuell.md`, siehe §6) <!-- d-check:ignore (geplante Datei) --> nennt die Befehle `testserver-up`, `-down`, `-logs`, `-status`, Host, Port, Benutzer, die Verschlüsselungsrichtlinie, die Beispiel-Tabellen mit ihren Spalten und je einen Beispielaufruf für zwei Clients, und jede Aussage über das Verhalten des Servers trägt einen Beleg (Test, Messung) oder ist als nicht gemessen gekennzeichnet.
- [ ] Die Grenzen stehen darin: was der Testserver nicht kann (zum Beispiel Anfragearten außerhalb der Spec, Last, Windows-Authentifizierung), dass es kein Produkt ist ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)), und dass die Testwerte nicht für Daten genutzt werden dürfen, die Schutz brauchen.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Verweis aus `README.md` und `harness/README.md` auf die Anleitung gesetzt; `make docs-check` grün.
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
| `docs/user/testserver-manuell.md` | neu | Anleitung (Ort siehe §6) <!-- d-check:ignore (geplante Datei) --> |
| `README.md` | update | Verweis |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Jeder Beispielaufruf wird vor der Aufnahme gegen einen laufenden `testserver-up` ausgeführt; die Ausgabe steht als Beleg im Slice §7.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-testserver-up-down](../in-progress/slice-testserver-up-down.md) liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Anleitung braucht mehr als zwei Client-Beispiele, weil jedes eigene Installationsschritte verlangt.
- `in-progress` → `open` (blockiert — Carveout?): ein Client lässt sich ohne Lizenzentscheidung des Nutzers nicht aufrufen (EULA).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, Review und Verifikation durchgeführt (die Verifikation führt die Beispielaufrufe selbst aus), Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Ort der Anleitung: `docs/user/` (Betrieb, Qualität) oder `docs/maintainer/` — Vorschlag `docs/user/`, weil sie Embedder beim Testen ihrer Einbettung nutzen; Verzeichnisse bestehen noch nicht <!-- d-check:ignore (Verzeichnisse entstehen mit der ersten Doku) --> — **Ausgang:** offen bis zur Nutzerentscheidung.
- Welche Clients die Beispiele nennen: ein Rohbytes-Beispiel ist ohne Lizenz möglich; `sqlcmd` und JDBC-Treiber bringen eigene Lizenz und Installation mit — **Ausgang:** offen bis zur Nutzerentscheidung (Vorschlag: Rohbytes-Client des Repos und ein JDBC-Beispiel mit Quellenangabe).
- Aussagen über das Verhalten gegenüber einem Client sind nur so weit belegt wie die Läufe von `make kompatibilitaet` (Referenzclient Microsoft.Data.SqlClient) — **Ausgang:** offen; die Anleitung kennzeichnet nicht gemessene Clients (AGENTS §3.8).

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

Noch nicht geschrieben; der Planner füllt die Sektion bei der Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** berührt wird nur Dokumentation des Default-Bereichs `*` (Kürzel `TDS`).

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (Aussagen über die Messung hinaus: Beispiele nur mit Beleg). Der Zähler steht in `observations/BEO-TDS/`; keine weiteren Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.


### Sub-Area: `*` (Default des Repos)

- **Modus:** GF
- **Konventionen-Dichte:** Greenfield-Deklaration in `harness/conventions.md`.
- **Phase-Reife:** Phase 3.
- **Evidenz-/Diskrepanz-Risiko:** niedrig (GF); Belege durch Ausführen der Beispiele.
- **Reconciliation-Aufwand:** keiner (GF).
